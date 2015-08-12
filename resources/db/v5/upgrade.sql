insert into data_source_query(data_source_id, query_id)
select d.id, q.id from query q, data_source d
;
