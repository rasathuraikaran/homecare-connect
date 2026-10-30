package com.homecare.payment;

import com.homecare.payment.PaymentService.StoredReceipt;
import com.homecare.user.User;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ReceiptController {

    private final PaymentService service;

    public ReceiptController(PaymentService service) {
        this.service = service;
    }

    /** A seeker sends the receipt of their bank transfer (the /api/bookings/** rule limits this to seekers). */
    @PostMapping(value = "/api/bookings/{id}/pay/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentInfo.ReceiptInfo> upload(@AuthenticationPrincipal User seeker, @PathVariable Long id,
                                                         @RequestPart("file") MultipartFile file,
                                                         @RequestParam(value = "note", required = false) String note) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadReceipt(seeker, id, file, note));
    }

    /** Admin only (see SecurityConfig). Never cached and never sniffed: it shows a bank account. */
    @GetMapping("/api/admin/bookings/{id}/receipts/{receiptId}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, @PathVariable Long receiptId) {
        StoredReceipt stored = service.receiptFile(id, receiptId);
        return ResponseEntity.ok()
                .contentType(stored.type())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(stored.resource());
    }
}
