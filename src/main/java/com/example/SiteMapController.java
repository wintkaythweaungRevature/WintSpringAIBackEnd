
package com.example;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class SiteMapController {
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public org.springframework.core.io.Resource getSitemap() {
        return new org.springframework.core.io.ClassPathResource("static/sitemap.xml");
    }
}
