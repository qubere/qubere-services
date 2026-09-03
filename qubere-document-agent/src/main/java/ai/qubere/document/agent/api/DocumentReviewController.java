package ai.qubere.document.agent.api;

import ai.qubere.document.agent.document.review.ExtractionReviewService;
import ai.qubere.document.agent.document.review.ReviewField;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The human-review surface over {@code ExtractionFieldEntity} readings, ported from what would have
 * been {@code extractionReview.ts}'s consuming route handler (the source file itself is pure logic
 * with no route of its own — see {@code ExtractionReviewFields}).
 */
@RestController
@RequestMapping("/api/documents/{documentId}/review")
public class DocumentReviewController {

    private final ExtractionReviewService reviewService;

    public DocumentReviewController(ExtractionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewField> listFields(@PathVariable String documentId) {
        return reviewService.reviewFieldsFor(documentId);
    }

    @PostMapping("/{fieldName}")
    public ResponseEntity<ReviewField> correctField(
            @PathVariable String documentId,
            @PathVariable String fieldName,
            @RequestBody Map<String, Object> request
    ) {
        Object rawValue = request.get("value");
        reviewService.submitCorrection(documentId, fieldName, rawValue == null ? null : String.valueOf(rawValue));

        return reviewService.reviewFieldsFor(documentId).stream()
                .filter(field -> field.fieldName().equals(fieldName))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
