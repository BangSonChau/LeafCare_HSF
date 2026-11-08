-- Payments now come from SePay (bank transfer QR), not MoMo: the bank transaction id is no longer MoMo's.
ALTER TABLE payment RENAME COLUMN momo_trans_id TO trans_id;
