create table if not exists events
(
    id serial primary key,
    name varchar (255) not null ,
    start_date date ,
    end_date date

);