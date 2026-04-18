package org.example.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// سنستخدم RestControllerAdvice لمعالجة الأخطاء عالمياً في هذه الخدمة
@RestControllerAdvice
public class InventoryNotFoundException {

    // تأكد أن هذا الكلاس (InventoryException) موجود فعلاً، أو استخدم RuntimeException للتجربة
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleInventoryError(RuntimeException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }
}