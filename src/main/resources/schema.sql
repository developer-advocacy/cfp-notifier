--drop table if exists events;
create table if not exists events (
    name varchar (255) not null ,
    start_date date ,
    end_date date,
    url varchar(255) not null ,
    primary key  (start_date,   name )
);

create table if not exists events_location (id serial primary  key  );