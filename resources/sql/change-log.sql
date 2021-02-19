-- liquibase formatted sql

-- changeset db:initial-schema
create table if not exists
  app_user(id int not null identity, nick varchar(50), password varchar(250),
           full_name varchar(100),
           active bit, primary key (id));

create table if not exists
  data_source(id int not null identity,
              name varchar(50) not null,
              user_name varchar(60),
              password varchar(255),
              url varchar(250) not null,
              app_user_id int,
              dbms varchar(50),
              schema varchar(50),
              primary key (id),
              foreign key (app_user_id) references app_user (id));

create table if not exists
  query(id int not null identity,
        name varchar(100),
        description varchar(250),
        sql text,
        app_user_id int,
        primary key (id),
        foreign key (app_user_id) references app_user(id));

-- create table if not exists
--   query_param(id int not null identity, query_id int, name varchar(50),
--               sql_type int,
--               primary key (id),
--               foreign key (query_id) references query(id));

create table if not exists
  data_source_query(data_source_id int not null
                    , query_id int not null,
                    primary key(data_source_id, query_id))
  ;

create table if not exists
  user_data_source(app_user_id int not null, data_source_id int not null,
                   primary key(app_user_id, data_source_id))
  ;

create table if not exists
  ds_table(id int auto_increment
           , name varchar(100) not null
           , alias varchar(100)
           , type varchar(20)
           , data_source_id int
           , primary key (id)
           , foreign key (data_source_id) references data_source(id)
           , unique (data_source_id, name) )
  ;

create table if not exists
  ds_column(id int auto_increment
            , table_id int
            , name varchar(100)
            , alias varchar(100)
            , data_type int
            , type_name varchar(50)
            , size int
            , digits int
            , nullable bit
            , is_pk bit
            , is_fk bit
            , fk_table varchar(100)
            , fk_column varchar(100)
            , primary key (id)
            , unique(table_id, name)
            , foreign key(table_id) references ds_table(id))
  ;

-- initial data
-- insert into app_user(nick, password, active)
-- values('admin', '$2a$11$E7b.iZnDDHygsZ7ACXdOtOKreLTxY7L1rGC6mKzXfKls7HQ.VXPLe', 1)
--        ;

-- changeset db:sql-metadata

alter table data_source
  add label varchar(255) default 'default';

alter table query
  add label varchar(255) default 'default';

alter table query
  add shared bit default 1;

-- changeset db:upgrade-queries runOnChange:true
update query
   set sql = '-- #' || name ||chr(10)||sql;

-- -- changeset db:add-uid-to-query
-- alter table query
--   add uid varchar(255);

