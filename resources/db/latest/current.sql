
create table data_source(id int,
    name varchar(50),
    user_name varchar(60),
    password varchar(20),
    url varchar(250));

create table query(id int,
    name varchar(100),
    description varchar(250),
    sql text);

create table query_param(id int, query_id int, name varchar(50), sql_type int);
