package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.balance.AvailableMonthDTO;
import com.jaoow.helmetstore.dto.sale.*;
import com.jaoow.helmetstore.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {
    private final SaleService saleService;

    @GetMapping
    public Page<SaleResponseDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            Principal principal) {
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));
        return saleService.findAll(pageable, principal);
    }

    @GetMapping("/{id}")
    public SaleDetailDTO getById(@PathVariable Long id, Principal principal) {
        return saleService.getById(id, principal);
    }

    @GetMapping("/history")
    public SaleHistoryResponse getHistory(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Principal principal) {
        return saleService.getHistory(year, month, principal);
    }

    /**
     * Get available months with sale counts (lightweight for month selector UI)
     */
    @GetMapping("/available-months")
    public List<AvailableMonthDTO> getAvailableMonths(Principal principal) {
        return saleService.getAvailableMonths(principal);
    }

    @PostMapping
    public SaleResponseDTO create(@RequestBody @Valid SaleCreateDTO saleCreateDTO, Principal principal) {
        return saleService.save(saleCreateDTO, principal);
    }

    @PutMapping("/{id}")
    public SaleResponseDTO update(@PathVariable Long id, @RequestBody @Valid SaleCreateDTO saleCreateDTO,
            Principal principal) {
        return saleService.update(id, saleCreateDTO, principal);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Principal principal) {
        saleService.delete(id, principal);
    }

    /**
     * Cancel a sale (total or partial) with optional refund
     */
    @PostMapping("/{id}/cancel")
    public SaleCancellationResponseDTO cancelSale(
            @PathVariable Long id,
            @RequestBody @Valid SaleCancellationRequestDTO request,
            Principal principal) {
        return saleService.cancelSale(id, request, principal);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id, Principal principal) {
        byte[] pdfContent = saleService.generateReceipt(id, principal);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "recibo-venda-" + id + ".pdf");
        headers.setContentLength(pdfContent.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}
