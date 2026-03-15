package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Handles posting video + caption + hashtags to each social platform API.
 * Each method returns the platform post ID on success.
 */
@Service
public class VideoPublishService {

    private static final Logger log = LoggerFactory.getLogger(VideoPublishService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Dispatch to the correct platform publisher.
     */
    public String publish(String platform, String accessToken, MultipartFile video,
                          String caption, String hashtags) throws Exception {
        return switch (platform.toLowerCase()) {
            case "youtube"   -> publishToYouTube(accessToken, video, caption, hashtags);
            case "instagram" -> publishToInstagram(accessToken, video, caption, hashtags);
            case "facebook"  -> publishToFacebook(accessToken, video, caption, hashtags);
            case "tiktok"    -> publishToTikTok(accessToken, video, caption, hashtags);
            case "linkedin"  -> publishToLinkedIn(accessToken, video, caption, hashtags);
            case "x"         -> publishToX(accessToken, video, caption, hashtags);
            default          -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
    }

    /* ── YouTube ───────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToYouTube(String accessToken, MultipartFile video,
                                    String caption, String hashtags) throws Exception {
        // Step 1: initiate resumable upload
        String initUrl = "https://www.googleapis.com/upload/youtube/v3/videos" +
            "?uploadType=resumable&part=snippet,status";

        HttpHeaders initHeaders = new HttpHeaders();
        initHeaders.setBearerAuth(accessToken);
        initHeaders.setContentType(MediaType.APPLICATION_JSON);
        initHeaders.set("X-Upload-Content-Type", video.getContentType());
        initHeaders.set("X-Upload-Content-Length", String.valueOf(video.getSize()));

        Map<String, Object> body = Map.of(
            "snippet", Map.of(
                "title", extractTitle(caption),
                "description", caption + "\n\n" + hashtags,
                "tags", hashtagsToList(hashtags),
                "categoryId", "22"
            ),
            "status", Map.of("privacyStatus", "public")
        );

        HttpEntity<Map<String, Object>> initRequest = new HttpEntity<>(body, initHeaders);
        ResponseEntity<Void> initResponse = restTemplate.exchange(initUrl, HttpMethod.POST, initRequest, Void.class);
        String uploadUrl = initResponse.getHeaders().getLocation().toString();

        // Step 2: upload video bytes
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.parseMediaType(video.getContentType()));
        ByteArrayResource videoResource = new ByteArrayResource(video.getBytes()) {
            @Override public String getFilename() { return video.getOriginalFilename(); }
        };
        HttpEntity<ByteArrayResource> uploadRequest = new HttpEntity<>(videoResource, uploadHeaders);
        ResponseEntity<Map> uploadResponse = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadRequest, Map.class);

        return String.valueOf(uploadResponse.getBody().get("id"));
    }

    /* ── Instagram (Graph API) ─────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToInstagram(String accessToken, MultipartFile video,
                                      String caption, String hashtags) throws Exception {
        // Get IG Business Account ID first
        String meUrl = "https://graph.instagram.com/me?fields=id&access_token=" + accessToken;
        ResponseEntity<Map> meResponse = restTemplate.getForEntity(meUrl, Map.class);
        String igUserId = String.valueOf(meResponse.getBody().get("id"));

        // Step 1: Create container
        String containerUrl = "https://graph.instagram.com/v18.0/" + igUserId + "/media";
        Map<String, String> containerBody = Map.of(
            "media_type", "REELS",
            "caption", caption + "\n\n" + hashtags,
            "access_token", accessToken
        );
        ResponseEntity<Map> containerResponse = restTemplate.postForEntity(containerUrl, containerBody, Map.class);
        String containerId = String.valueOf(containerResponse.getBody().get("id"));

        // Step 2: Publish container
        String publishUrl = "https://graph.instagram.com/v18.0/" + igUserId + "/media_publish";
        Map<String, String> publishBody = Map.of(
            "creation_id", containerId,
            "access_token", accessToken
        );
        ResponseEntity<Map> publishResponse = restTemplate.postForEntity(publishUrl, publishBody, Map.class);
        return String.valueOf(publishResponse.getBody().get("id"));
    }

    /* ── Facebook ──────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToFacebook(String accessToken, MultipartFile video,
                                     String caption, String hashtags) throws Exception {
        // Get page ID
        String pagesUrl = "https://graph.facebook.com/v18.0/me/accounts?access_token=" + accessToken;
        ResponseEntity<Map> pagesResponse = restTemplate.getForEntity(pagesUrl, Map.class);
        var pages = (java.util.List<Map<String, Object>>) pagesResponse.getBody().get("data");
        if (pages == null || pages.isEmpty()) throw new RuntimeException("No Facebook pages found");

        String pageId = String.valueOf(pages.get(0).get("id"));
        String pageToken = String.valueOf(pages.get(0).get("access_token"));

        // Upload video to page
        String uploadUrl = "https://graph.facebook.com/v18.0/" + pageId + "/videos";
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("description", caption + "\n\n" + hashtags);
        form.add("access_token", pageToken);
        form.add("source", new ByteArrayResource(video.getBytes()) {
            @Override public String getFilename() { return video.getOriginalFilename(); }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(form, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, request, Map.class);
        return String.valueOf(response.getBody().get("id"));
    }

    /* ── TikTok ────────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToTikTok(String accessToken, MultipartFile video,
                                   String caption, String hashtags) throws Exception {
        // TikTok Content Posting API v2
        String initUrl = "https://open.tiktokapis.com/v2/post/publish/video/init/";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> postInfo = Map.of(
            "title", extractTitle(caption),
            "privacy_level", "PUBLIC_TO_EVERYONE",
            "disable_duet", false,
            "disable_comment", false,
            "disable_stitch", false,
            "video_cover_timestamp_ms", 1000
        );
        Map<String, Object> sourceInfo = Map.of(
            "source", "FILE_UPLOAD",
            "video_size", video.getSize(),
            "chunk_size", video.getSize(),
            "total_chunk_count", 1
        );
        Map<String, Object> body = Map.of("post_info", postInfo, "source_info", sourceInfo);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(initUrl, request, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        String publishId = String.valueOf(data.get("publish_id"));
        String uploadUrl = String.valueOf(data.get("upload_url"));

        // Upload video chunk
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.parseMediaType(video.getContentType()));
        uploadHeaders.set("Content-Range", "bytes 0-" + (video.getSize() - 1) + "/" + video.getSize());
        ByteArrayResource videoResource = new ByteArrayResource(video.getBytes()) {
            @Override public String getFilename() { return video.getOriginalFilename(); }
        };
        restTemplate.exchange(uploadUrl, HttpMethod.PUT, new HttpEntity<>(videoResource, uploadHeaders), Void.class);
        return publishId;
    }

    /* ── LinkedIn ──────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToLinkedIn(String accessToken, MultipartFile video,
                                     String caption, String hashtags) throws Exception {
        // Get LinkedIn person URN
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> meResponse = restTemplate.exchange(
            "https://api.linkedin.com/v2/me", HttpMethod.GET, entity, Map.class);
        String personUrn = "urn:li:person:" + meResponse.getBody().get("id");

        // Register upload
        String registerUrl = "https://api.linkedin.com/v2/assets?action=registerUpload";
        Map<String, Object> registerBody = Map.of(
            "registerUploadRequest", Map.of(
                "owner", personUrn,
                "recipes", java.util.List.of("urn:li:digitalmediaRecipe:feedshare-video"),
                "serviceRelationships", java.util.List.of(
                    Map.of("identifier", "urn:li:userGeneratedContent", "relationshipType", "OWNER")
                )
            )
        );
        headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> registerResponse = restTemplate.exchange(
            registerUrl, HttpMethod.POST, new HttpEntity<>(registerBody, headers), Map.class);

        Map<String, Object> value = (Map<String, Object>) registerResponse.getBody().get("value");
        Map<String, Object> uploadMechanism = (Map<String, Object>) value.get("uploadMechanism");
        Map<String, Object> uploadRequest = (Map<String, Object>)
            uploadMechanism.get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest");
        String uploadUrl = String.valueOf(uploadRequest.get("uploadUrl"));
        String assetUrn = String.valueOf(value.get("asset"));

        // Upload video
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(accessToken);
        uploadHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        restTemplate.exchange(uploadUrl, HttpMethod.POST,
            new HttpEntity<>(video.getBytes(), uploadHeaders), Void.class);

        // Create post
        String shareUrl = "https://api.linkedin.com/v2/ugcPosts";
        Map<String, Object> shareBody = Map.of(
            "author", personUrn,
            "lifecycleState", "PUBLISHED",
            "specificContent", Map.of(
                "com.linkedin.ugc.ShareContent", Map.of(
                    "shareCommentary", Map.of("text", caption + "\n\n" + hashtags),
                    "shareMediaCategory", "VIDEO",
                    "media", java.util.List.of(Map.of(
                        "status", "READY", "media", assetUrn,
                        "title", Map.of("text", extractTitle(caption))
                    ))
                )
            ),
            "visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC")
        );
        HttpHeaders shareHeaders = new HttpHeaders();
        shareHeaders.setBearerAuth(accessToken);
        shareHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> shareResponse = restTemplate.exchange(
            shareUrl, HttpMethod.POST, new HttpEntity<>(shareBody, shareHeaders), Map.class);
        return String.valueOf(shareResponse.getBody().get("id"));
    }

    /* ── X (Twitter) ───────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    private String publishToX(String accessToken, MultipartFile video,
                               String caption, String hashtags) throws Exception {
        // Step 1: Upload media (INIT)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> initParams = new LinkedMultiValueMap<>();
        initParams.add("command", "INIT");
        initParams.add("total_bytes", String.valueOf(video.getSize()));
        initParams.add("media_type", video.getContentType());
        initParams.add("media_category", "tweet_video");

        ResponseEntity<Map> initResponse = restTemplate.exchange(
            "https://upload.twitter.com/1.1/media/upload.json",
            HttpMethod.POST, new HttpEntity<>(initParams, headers), Map.class);
        String mediaId = String.valueOf(initResponse.getBody().get("media_id_string"));

        // Step 2: APPEND chunk
        MultiValueMap<String, Object> appendForm = new LinkedMultiValueMap<>();
        appendForm.add("command", "APPEND");
        appendForm.add("media_id", mediaId);
        appendForm.add("segment_index", "0");
        appendForm.add("media", new ByteArrayResource(video.getBytes()) {
            @Override public String getFilename() { return video.getOriginalFilename(); }
        });
        HttpHeaders appendHeaders = new HttpHeaders();
        appendHeaders.setBearerAuth(accessToken);
        appendHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("https://upload.twitter.com/1.1/media/upload.json",
            HttpMethod.POST, new HttpEntity<>(appendForm, appendHeaders), Void.class);

        // Step 3: FINALIZE
        MultiValueMap<String, String> finalizeParams = new LinkedMultiValueMap<>();
        finalizeParams.add("command", "FINALIZE");
        finalizeParams.add("media_id", mediaId);
        restTemplate.exchange("https://upload.twitter.com/1.1/media/upload.json",
            HttpMethod.POST, new HttpEntity<>(finalizeParams, headers), Map.class);

        // Step 4: Create tweet
        Map<String, Object> tweetBody = Map.of(
            "text", caption + " " + hashtags,
            "media", Map.of("media_ids", java.util.List.of(mediaId))
        );
        HttpHeaders tweetHeaders = new HttpHeaders();
        tweetHeaders.setBearerAuth(accessToken);
        tweetHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> tweetResponse = restTemplate.exchange(
            "https://api.twitter.com/2/tweets",
            HttpMethod.POST, new HttpEntity<>(tweetBody, tweetHeaders), Map.class);

        Map<String, Object> data = (Map<String, Object>) tweetResponse.getBody().get("data");
        return String.valueOf(data.get("id"));
    }

    /* ── Helpers ───────────────────────────────────────────────── */
    private String extractTitle(String caption) {
        if (caption == null || caption.isBlank()) return "New Video";
        String firstLine = caption.split("\n")[0].trim();
        return firstLine.length() > 100 ? firstLine.substring(0, 97) + "..." : firstLine;
    }

    private java.util.List<String> hashtagsToList(String hashtags) {
        if (hashtags == null || hashtags.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(hashtags.split("\\s+"))
            .filter(t -> t.startsWith("#"))
            .map(t -> t.substring(1))
            .collect(java.util.stream.Collectors.toList());
    }
}
