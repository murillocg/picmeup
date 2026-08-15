package com.picmeup.common;

import com.picmeup.photo.EventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

    private final EventService eventService;

    public SitemapController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        sb.append("  <url><loc>https://elitesportphotos.com/</loc><priority>1.0</priority></url>\n");
        sb.append("  <url><loc>https://elitesportphotos.com/faq</loc><priority>0.5</priority></url>\n");
        sb.append("  <url><loc>https://elitesportphotos.com/privacy-policy</loc><priority>0.3</priority></url>\n");

        for (var event : eventService.listActiveEvents()) {
            sb.append("  <url><loc>https://elitesportphotos.com/events/")
              .append(event.getSlug())
              .append("</loc><lastmod>")
              .append(event.getDate())
              .append("</lastmod><priority>0.8</priority></url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }
}
