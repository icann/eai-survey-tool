-- find_list_to_search
select * from mx where status_4 = 'N' or status_6 = 'N'

-- insert_mx_ip
insert ignore into mx_ip values(?, ?)

-- update_mx_searched_4
update mx set status_4 = ? where mx = ?

-- update_mx_searched_6
update mx set status_6 = ? where mx = ?

-- create_ip
insert into ip (ip, status) select distinct ip, 'N' from mx_ip
