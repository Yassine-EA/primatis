-- =============================================================
-- DEV-09.3 — Paramètres métier Fine (DEV-DEC-0046)
-- =============================================================
--
-- Ajoute les deux paramètres métier globaux nécessaires au futur calcul
-- automatique du montant d'une Fine (DEV-DEC-0046,
-- documentation-interne//DEV-09.2 — DECISIONS FINES.md) : tarif hebdomadaire et
-- plafond. Cette migration ne fait qu'ajouter les deux clés à
-- application_setting (structure inchangée, value_type DECIMAL déjà
-- autorisé par ck_application_setting_value_type, V001) — le calcul
-- lui-même (LoanService.registerReturn / futur FineService) reste hors
-- périmètre de DEV-09.3.

INSERT INTO application_setting (setting_key, setting_value, value_type, description, updated_at)
VALUES
    ('FINE_WEEKLY_RATE', '0.80', 'DECIMAL',
     'Tarif appliqué pour chaque semaine de retard entamée.', now()),
    ('FINE_MAX_AMOUNT', '25.00', 'DECIMAL',
     'Montant maximal d''une amende pour un Loan.', now());
