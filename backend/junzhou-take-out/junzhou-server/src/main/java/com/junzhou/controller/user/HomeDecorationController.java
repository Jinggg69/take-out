package com.junzhou.controller.user;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.decoration.HomeDecorationService;
import com.junzhou.decoration.HomeDecorationService.PublicState;
import com.junzhou.result.Result;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("publicHomeDecorationController")
@MiniappEntry(MiniappEntry.Scope.PUBLIC)
@RequestMapping("/user/stores")
public class HomeDecorationController {
    private final HomeDecorationService service;

    public HomeDecorationController(HomeDecorationService service) { this.service = service; }

    @GetMapping("/{storeId}/decoration")
    public ResponseEntity<Result<PublicState>> read(@PathVariable String storeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Result.success(service.publicHome(storeId)));
    }
}
