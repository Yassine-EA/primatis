-- =============================================================
-- DEV-07.10 — Référence UTC explicite des contraintes temporelles Loan
-- =============================================================
--
-- Cause racine (gate final DEV-07, documentation-interne//DEV-07 - Prets et
-- retours - Audit final.md §8) : loan.loan_date::date est un cast
-- TIMESTAMPTZ -> DATE dont le résultat dépend de la timezone de
-- session PostgreSQL (réellement Europe/Brussels, jamais UTC dans
-- application.yml), alors que LoanService calcule due_date/return_date
-- exclusivement via Clock.systemUTC() (ClockConfig, inchangé par cette
-- migration). Pendant la fenêtre quotidienne où le jour calendaire
-- Bruxelles est déjà "demain" alors qu'UTC reste "aujourd'hui" (1h en
-- CET, 2h en CEST), loan_date::date pouvait dépasser due_date/
-- return_date calculés en UTC, rejetant une opération Loan légitime.
--
-- Correction : convertir explicitement loan_date en UTC avant le cast
-- ::date, indépendamment de la timezone de session PostgreSQL (DEV-DEC-0035).
-- Ne modifie ni ClockConfig, ni la timezone globale PostgreSQL
-- (interdits par la décision de correction) : uniquement les deux
-- contraintes elles-mêmes.

ALTER TABLE loan
    DROP CONSTRAINT ck_loan_due_date_after_loan_date;

ALTER TABLE loan
    DROP CONSTRAINT ck_loan_return_date_after_loan_date;

ALTER TABLE loan
    ADD CONSTRAINT ck_loan_due_date_after_loan_date
        CHECK (due_date >= (loan_date AT TIME ZONE 'UTC')::date);

ALTER TABLE loan
    ADD CONSTRAINT ck_loan_return_date_after_loan_date
        CHECK (
            return_date IS NULL
            OR return_date >= (loan_date AT TIME ZONE 'UTC')::date
        );
