package com.iavtar.gfj_be.service;

import com.iavtar.gfj_be.entity.Quotation;
import com.iavtar.gfj_be.entity.ShippingTracker;
import com.iavtar.gfj_be.model.request.UpdateShippingTrackingRequest;
import com.iavtar.gfj_be.model.response.ServiceResponse;
import com.iavtar.gfj_be.repository.ClientRepository;
import com.iavtar.gfj_be.repository.QuotationRepository;
import com.iavtar.gfj_be.repository.ShippingServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

import com.iavtar.gfj_be.model.response.PagedUserResponse;

@Slf4j
@Service
public class ShippingServiceImpl implements ShippingService {

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private ShippingServiceRepository shippingRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ShippingIdGeneratorService shippingIdGeneratorService;

    @Override
    public ResponseEntity<?> markQuotationsForShipping(List<String> quotations) {
        String shippingId = shippingIdGeneratorService.generateId();
        try {
            quotations.forEach(quotation -> quotationRepository.findByQuotationId(quotation)
                    .ifPresent(qt -> {
                        qt.setShippingId(shippingId);
                        quotationRepository.save(qt);
                    })
            );
            shippingRepository.save(ShippingTracker.builder().shippingId(shippingId).status("pending").build());
            return new ResponseEntity<>(ServiceResponse.builder().message("Marked For Shipping").build(), HttpStatus.OK);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> getAllShipping(int offset, int size) {
        try {
            List<Quotation> quotations = quotationRepository.findAll();
            List<Quotation> shippableQuotations = quotations.stream()
                    .filter(quotation -> Objects.nonNull(quotation.getShippingId()))
                    .toList();
            Map<String, List<Quotation>> grouped = shippableQuotations.stream()
                    .collect(Collectors.groupingBy(Quotation::getShippingId));
            List<ShippingTracker> shippingTrackers = shippingRepository.findAll();
            Map<String, String> shippingStatusById = shippingTrackers.stream()
                    .collect(Collectors.toMap(ShippingTracker::getShippingId, ShippingTracker::getStatus, (a, b) -> a));
            Map<String, String> shippingTrackingIdById = shippingTrackers.stream()
                    .collect(Collectors.toMap(
                            ShippingTracker::getShippingId,
                            st -> st.getTrackingId() != null ? st.getTrackingId() : "",
                            (a, b) -> a
                    ));
            List<Map<String, Object>> shippingGroups = grouped.entrySet().stream()
                    .map(entry -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("shippingId", entry.getKey());
                        item.put("quotations", entry.getValue());
                        item.put("count", entry.getValue().size());
                        item.put("status", shippingStatusById.get(entry.getKey()));
                        item.put("trackingId", shippingTrackingIdById.get(entry.getKey())); // can be null
                        return item;
                    })
                    .collect(Collectors.toList());
            int total = shippingGroups.size();
            int start = Math.min(Math.max(offset, 0), total);
            int end = Math.min(start + Math.max(size, 1), total);
            List<Map<String, Object>> pageContent = shippingGroups.subList(start, end);
            int pageNumber = size > 0 ? start / size : 0;
            Pageable pageable = PageRequest.of(pageNumber, Math.max(size, 1));
            Page<Map<String, Object>> page = new PageImpl<>(pageContent, pageable, total);
            PagedUserResponse<Map<String, Object>> response = PagedUserResponse.from(page, offset, Math.max(size, 1));
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> addTrackingId(String shippingId, String trackingId) {
        try {
            log.info("Adding tracking ID {} to shipping ID: {}", trackingId, shippingId);
            if (shippingId == null || shippingId.trim().isEmpty()) {
                log.error("Validation failed - shipping ID is required");
                ServiceResponse errorResponse = ServiceResponse.builder()
                        .message("Shipping ID is required")
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (trackingId == null || trackingId.trim().isEmpty()) {
                log.error("Validation failed - tracking ID is required");
                ServiceResponse errorResponse = ServiceResponse.builder()
                        .message("Tracking ID is required")
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
            Optional<ShippingTracker> shippingTrackerOptional = shippingRepository.findByShippingId(shippingId);
            if (shippingTrackerOptional.isEmpty()) {
                log.error("Shipping tracker not found with shipping ID: {}", shippingId);
                ServiceResponse errorResponse = ServiceResponse.builder()
                        .message("Shipping tracker not found with shipping ID: " + shippingId)
                        .build();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            ShippingTracker shippingTracker = shippingTrackerOptional.get();
            shippingTracker.setTrackingId(trackingId);
            shippingTracker.setStatus("shipped");
            shippingRepository.save(shippingTracker);
            List<Quotation> quotations = quotationRepository.findAllByShippingId(shippingId);
            quotations.forEach(quotation -> {
                quotation.setQuotationStatus("shipped");
                quotationRepository.save(quotation);
            });
            log.info("Successfully set tracking ID {} on shipping tracker: {}", trackingId, shippingId);
            ServiceResponse response = ServiceResponse.builder()
                    .message("Tracking ID added successfully")
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            log.error("Error adding tracking ID: {}", exception.getMessage(), exception);
            ServiceResponse errorResponse = ServiceResponse.builder()
                    .message("Error adding tracking ID: " + exception.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @Override
    public ResponseEntity<?> updateTrackingStatus(UpdateShippingTrackingRequest request) {
        try {
            Optional<ShippingTracker> shippingTracker = shippingRepository.findByShippingId(request.getShippingId());
            if (shippingTracker.isPresent()) {
                ShippingTracker st = shippingTracker.get();
                st.setStatus(request.getStatus());
                shippingRepository.save(st);
                List<Quotation> quotations = quotationRepository.findAllByShippingId(request.getShippingId());
                quotations.forEach(quotation -> {
                    quotation.setQuotationStatus(request.getStatus());
                    quotationRepository.save(quotation);
                });
                return new ResponseEntity<>(ServiceResponse.builder().message("Shipping Status Updated!").build(), HttpStatus.OK);
            }
            return new ResponseEntity<>(ServiceResponse.builder().message("Shipping Status Not Updated!").build(), HttpStatus.BAD_REQUEST);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

}
