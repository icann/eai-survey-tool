-- find_list_to_search
select ip from ip where timestamp is null order by rand()

-- update_ip_results
update ip set
    header = ?,
    ehlo_success = ?,
    ehlo_result = ?,
    ascii_success = ?,
    ascii_result = ?,
    idn_success = ?,
    idn_result = ?,
    timestamp = current_timestamp
    where ip = ?
