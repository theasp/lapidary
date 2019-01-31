# Lapidary

A search engine for structured data using Postgres, with a similar syntax to Apache Lucene.  This is intended for small to medium sized centralized logging configurations.

Pull requests welcome, especially documentation!

[![CircleCI](https://circleci.com/gh/theasp/lapidary.svg?style=svg)](https://circleci.com/gh/theasp/lapidary)

## Features

* Data is stored as JSON in PostgreSQL
* Lucene like search syntax
* Full text search

## Screenshots

### Tables
![Tables](screenshots/tables.png)

### Query
![Query](screenshots/query.png)

## Installation

### Docker

Using the [docker-compose.yml](docker-compose.yml) file from the repository, run:
```
docker-compose up
```

This will start PostgreSQL, Lapidary and fluentd.  You can log into Lapidary at http://localhost:8081 using the username admin and the password "ChangeMe!".  Fluentd is listening on 24230 for forward connections, and 5145 for syslog.

You can log a message using:
```
logger -n localhost -P 5145 "Hello Lapidary!"
```

## Configuration

## Reference

### Query String

Querying the database is loosely based on Lucene syntax, though grouping is done in a more lisp like style.

The query consists fields to search, and the values to search for. If your value contains whitespace, you can wrap it in quotation marks.  For example, to find all records which contain the word `fail` in the `message` field, you could use:
```
message:fail
```

By default, each field you search is joined together logically using `or`, so the following will find records which have `fail` or `ssh` in the `message field:
```
message:fail message:ssh
```

This could also be written using a list of acceptable values:
```
message:(fail ssh)
```

You can build more complex searches using `and` and `or`, with `or` being the default, so that the above search will logically expand to be:
```
(or message:fail message:ssh)
```

If you were looking for records that match both `fail` and `ssh`:
```
(and message:fail message:ssh)
```

A term can be negated by prepending it with `!`:, so to find records with `fail` and `ssh`, but not `mysql` in `message`, you could use:
```
(and message:fail message:ssh !message:mysql)
```

You can specify a comparison operator, using one of `<`, `<=`, `>`, `>=`, `=`, `!=`.  For example, to find records with `OriginStatus` between 400 and 499, you could use:
```
(and OriginStatus:>=400 OriginStatus:<=499)
```

For `=` and `!=` the value will be matched exactly.  For example, to match records with a `RequestMethod` of `POST` but not `POSTAL`, you can use:
```
RequestMethod:=POST
```

Addtionally, there are regex operators of `~` for a case sensitve regex, `~*` for a case insensntive regex, and the negative form of both using `!~` or `!~*`.  If you want to match the regex `/sockjs/.*/websocket$` in a `RequestPath` field, you could use:
```
RequestPath:~"sockjs/.*/websocket$"
```

The comparison operators can also be used in a list:
```
RequestMethod:(=POST =HEAD)
```

As an alternative to using `and`, you can use a range of values with `[]` used to signify both ends of the range being inclusive, `{}` for exclusive, and `[}` or `{]` as a mix of both.  The values are seperated using either `..` or ` to `.  For `OriginStatus` between 400 and 499, you could use:
```
OriginStatus:[400 to 499]
```

Or, for the same effect:
```
OriginStatus:[400..500}
```

### API

TODO: Currently not available

## Project Roadmap
