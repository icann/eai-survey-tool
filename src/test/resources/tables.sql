create table progress (
    id          int             auto_increment,
    name        varchar(100)    not null,
    timestamp   timestamp       null default null,
    constraint pk_progress primary key(id)
);
insert into progress(name) values('Process started');
insert into progress(name) values('Search MX Servers');
insert into progress(name) values('Resolve IPv4 MX Servers');
insert into progress(name) values('Resolve IPv6 MX Servers');
insert into progress(name) values('Probe MX Servers');
insert into progress(name) values('Resolve GeoIP Location');

create table record (
    owner       varchar(255)    not null,
    zone        varchar(63)     not null,
    status      char(1)         not null, -- N = No Searched, S = Searched, X = Empty, # = RCode, E = Error
    constraint pk_record primary key(owner)
);

create table record_mx (
    owner   varchar(255)    not null,
    mx      varchar(255)    not null,
    constraint pk_record_mx primary key (owner, mx)
);

create table mx (
    mx          varchar(255)    not null,
    status_4    char(1)         not null,
    status_6    char(1)         not null,
    constraint pk_mx primary key(mx)
);

create table mx_ip (
    mx      varchar(255)    not null,
    ip      varchar(39)     not null,
    constraint pk_mx_ip primary key (mx, ip)
);

create table ip (
    ip              varchar(39)     not null,
    header          text(32768)             ,
    ehlo_result     text(4000)              ,
    ehlo_success    char(1)                 ,
    ascii_result    text(4000)              ,
    ascii_success   char(1)                 ,
    idn_result      text(4000)              ,
    idn_success     char(1)                 ,
    country         char(2)                 ,
    timestamp       timestamp       null default null,
    constraint pk_ip primary key (ip)
);