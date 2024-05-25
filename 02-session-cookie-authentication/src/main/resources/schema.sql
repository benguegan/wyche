drop table if exists users;
create table users(
  user_id varchar(30) primary key, 
  pw_hash varchar(255) not null
);

drop table if exists spaces;
create table spaces(
  space_id int primary key, 
  name varchar(255) not null, 
  owner varchar(30) not null
);
create sequence space_id_seq;

drop table if exists messages;
create table messages(
  space_id int not null references spaces(space_id), 
  msg_id int primary key,
  author varchar(30) not null, 
  msg_time timestamp not null default current_timestamp, 
  msg_text varchar(1024) not null
);
create sequence msg_id_seq;
create index msg_timestamp_idx on messages(msg_time);
create unique index space_name_idx on spaces(name);

drop table if exists audit_log;
create table audit_log(
  audit_id int null, 
  method varchar(10) not null, 
  path varchar(100) not null, 
  user_id varchar(30) null, 
  status int null, 
  audit_time timestamp not null
);
create sequence audit_id_seq;

drop table if exists permissions;
create table permissions(
  space_id int not null references spaces(space_id), 
  user_id varchar(30) not null references users(user_id), 
  perms varchar(3) not null, 
  primary key (space_id, user_id)
);

create user api_user PASSWORD 'password';
grant select, insert on spaces, messages to api_user;
grant delete on messages to api_user;
grant select, insert on users to api_user;
grant select, insert on audit_log to api_user;
grant select, insert on permissions to api_user;
