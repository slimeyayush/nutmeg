package com.example.nutmeg.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * /r/PXLQ is a React route, not a file. Forward it to index.html so a hard
 * refresh or a link pasted in the group chat works. Deliberately narrow — no
 * catch-all — so /api and /ws are untouched.
 */
@Controller
public class SpaController {

    @GetMapping("/r/{code:[A-Za-z0-9]+}")
    public String room() {
        return "forward:/index.html";
    }
}
