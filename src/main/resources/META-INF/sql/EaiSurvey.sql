-- get_progress
select * from progress where timestamp is null order by id limit 1

-- update_progress
update progress set timestamp = current_timestamp where id = ?

-- get_all_ips
select ip from ip;