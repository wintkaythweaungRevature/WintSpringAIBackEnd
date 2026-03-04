package com.example;

public package com.example;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SiteMapController {

    // ဤနေရာတွင် /api/ai လုံးဝ မပါရပါ။
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public Resource getSitemap() {
        return new ClassPathResource("static/sitemap.xml");
    }
} {
    
}
