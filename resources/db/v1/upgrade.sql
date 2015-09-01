create table app_user(id int not null identity, nick varchar(50), password varchar(250),
full_name varchar(100),
active bit, primary key (id));

create table data_source(id int not null identity,
name varchar(50) not null,
dbms varchar(50) not null,
user_name varchar(60),
password varchar(255),
url varchar(250) not null,
app_user_id int,
schema varchar(50),
primary key (id),
foreign key (app_user_id) references app_user (id));

create table query(id int not null identity,
name varchar(100),
description varchar(250),
sql text,
app_user_id int,
primary key (id),
foreign key (app_user_id) references app_user(id));

create table query_param(id int not null identity, query_id int, name varchar(50),
sql_type int,
primary key (id),
foreign key (query_id) references query(id));

create table data_source_query(data_source_id int not null
, query_id int not null,
primary key(data_source_id, query_id))
;

create table user_data_source(app_user_id int not null, data_source_id int not null,
primary key(app_user_id, data_source_id))
;


-- initial data
insert into app_user(nick, password, active)
values('admin', '$2a$11$E7b.iZnDDHygsZ7ACXdOtOKreLTxY7L1rGC6mKzXfKls7HQ.VXPLe', 1)
;
