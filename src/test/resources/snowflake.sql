create table progress (
    run         int,
    id          int,
    name        varchar(100),
    timestamp   timestamp
);

create table record (
    run         int,
    owner       varchar(255) ,
    zone        varchar(63),
    status      char(1)
);

create table record_mx (
    run         int,
    owner       varchar(255),
    mx          varchar(255)
);

create table mx (
    run         int,
    mx          varchar(255) ,
    status_4    char(1),
    status_6    char(1)
);

create table mx_ip (
    run         int,
    mx          varchar(255),
    ip          varchar(39)
);

create table ip (
    run             int,
    ip              varchar(39),
    header          text(32768),
    ehlo_result     text(8000),
    ehlo_success    char(1),
    ascii_result    text(8000),
    ascii_success   char(1),
    idn_result      text(8000),
    idn_success     char(1),
    country         char(2),
    timestamp       timestamp
);