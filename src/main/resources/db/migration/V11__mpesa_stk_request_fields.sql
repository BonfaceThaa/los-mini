ALTER TABLE mpesa_stk_push_requests
    CHANGE COLUMN provider_request_id merchant_request_id VARCHAR(100);

ALTER TABLE mpesa_stk_push_requests
    ADD COLUMN checkout_request_id VARCHAR(100) NULL AFTER merchant_request_id,
    ADD COLUMN provider_customer_message VARCHAR(500) NULL AFTER provider_response_description;

CREATE INDEX idx_mpesa_stk_request_checkout ON mpesa_stk_push_requests (checkout_request_id);
