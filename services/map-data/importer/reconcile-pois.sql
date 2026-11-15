WITH pending AS MATERIALIZED (
    SELECT record.id FROM places.poi_records record
    JOIN places.providers provider ON provider.code = record.provider_code
    WHERE record.record_status = 'current' AND provider.approval_status = 'approved'
        AND NOT EXISTS (SELECT 1 FROM places.poi_links link
            WHERE link.source_record_id = record.id AND link.match_status = 'confirmed')
    ORDER BY record.first_seen_at, record.provider_code, record.external_id
), reconciled AS (
    SELECT result.outcome FROM pending CROSS JOIN LATERAL places.reconcile_record(pending.id) result
)
SELECT outcome, count(*) FROM reconciled GROUP BY outcome ORDER BY outcome;

SELECT link.source_record_id, link.poi_id, record.provider_code, record.external_id, record.name,
    link.match_method, link.confidence
FROM places.poi_links link JOIN places.poi_records record ON record.id = link.source_record_id
WHERE link.match_status = 'proposed'
ORDER BY record.provider_code, record.external_id;