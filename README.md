# Data Explorer

A multi-user web-based database query tool.

## Installation

Download from https://github.com/rinconjc/data-explorer/releases/download/alpha/data-explorer-0.1.jar

## Usage

    $ java -Dconf=<path-to-config> -jar data-explorer-0.1.jar [port-number]
    
 Sample configuration:
 
 ```
 {:app-env "test"
 :db {
      :db "/tmp/data-explorer"
      :user "sa"
      :password "sa"
      }
 :secret-key "replace-this-with-custom-key"
 }
```
Point browser at http://localhost:port 
Register a new account and login!


## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
