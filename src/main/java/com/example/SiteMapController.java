package com.example;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SiteMapController {

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
@ResponseBody
public String getSitemap() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
           "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" +
           "  <url>\n" +
           "    <loc>https://wintaibot.com/</loc>\n" +
           "    <lastmod>2026-03-04</lastmod>\n" +
           "    <priority>1.0</priority>\n" +
           "  </url>\n" +
           "</urlset>";
}
} 
