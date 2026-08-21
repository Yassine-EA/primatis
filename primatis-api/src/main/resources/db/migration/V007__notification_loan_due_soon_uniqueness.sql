-- =============================================================
-- DEV-10.3 — Anti-doublon LOAN_DUE_SOON (DEV-DEC-0054)
-- =============================================================
--
-- Protège en base l'invariant métier FIGÉ « maximum une Notification
-- LOAN_DUE_SOON par Loan » (business-rules.md §6.7/§10.6,
-- database-model.md §13.7), même si le futur scheduler LOAN_DUE_SOON
-- (DEV-10.6+) est rejoué plusieurs fois sur le même Loan. Index unique
-- partiel, même style que ux_loan_open_copy / ux_reservation_active_user_title
-- / ux_reservation_ready_assigned_copy (V001) : seules les lignes où
-- notification_type = 'LOAN_DUE_SOON' sont concernées par l'unicité —
-- un même Loan peut toujours posséder par ailleurs une Notification
-- LOAN_OVERDUE et/ou LOAN_RETURNED sans violation.
--
-- Aucun NotificationService/scheduler n'est introduit par cette
-- migration : uniquement la protection structurelle, dernier filet
-- d'intégrité en concurrence (le futur Service testera l'existence au
-- préalable via NotificationRepository.existsByLoanIdAndNotificationType
-- pour rester idempotent sans dépendre uniquement de la capture
-- d'exception SQL).

CREATE UNIQUE INDEX ux_notification_loan_due_soon
    ON notification (loan_id)
    WHERE notification_type = 'LOAN_DUE_SOON';
