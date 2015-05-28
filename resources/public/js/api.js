angular.module('dbquery.api',['ngResource'])
    .factory('Api', function($resource){
        var tablesRes = $
        resource('/ds/')
        return {
            getTables:function(ds){
                
            }
            
            
        };        
    });
