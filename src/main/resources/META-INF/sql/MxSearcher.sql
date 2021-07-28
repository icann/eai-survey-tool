-- find_list_to_search
select * from record where status = 'N' or status = 'E'

-- insert_record_mx
insert ignore into record_mx values(?, ?)

-- update_record_searched
update record set status = ? where owner = ?

-- create_mx
insert into mx (mx, status_4, status_6) select distinct mx, 'N', 'N' from record_mx
