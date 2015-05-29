angular.module('dbquery.api',['ngResource'])
    .factory('Api', function($resource, $http, $q){
        var resCache = {};
        function getResource(key, path){
            var r = resCache[key];
            if(r) return r;
            r = $resource(path, null, {
                'update': {method:'PUT'}
            });
            resCache[key]=r;
            return r;
        }

        function toPromise(r){
            var q =$q.defer();
            r.success(function(res){
                q.resolve(res);
            }).error(function(err){
                q.reject(err);
            });
            return q.promise;
        }

        function getDsResource(ds, res){
            return getResource(ds+res, '/ds/'+ds+'/'+ res +'/:id');
        }
        var dsResource = getResource('data-source','/data-source/:id');
        var qryResource = getResource('queries', '/queries/:id');
        var usrResource = getResource('users','/users/:id');

        return {
            getDatasources: function(){
                return dsResource.query();
            },
            saveDatasource:function(ds){
                if(ds.id)
                    return toPromise(dsResource.update(ds));
                else
                    return toPromise(dsResource.save(ds));
            },
            deleteDatasource:function(dsId){
                return toPromise(dsResource.delete({id:dsId}));
            },
            getTables:function(ds){
                return getDsResource(ds, 'tables').query();
            },
            getViews:function(ds){
                return getDsResource(ds, 'views').query();
            },
            executeSql:function(ds,sql){
                return toPromise()
                var q = $q.defer($http.post('/ds/'+ds+'/execute', {"raw-sql":sql}));
            },
            getQueries:function(ds){
                return getDsResource(ds, 'queries').query();
            },
            saveQuery:function(query){
                if(query.id){
                    return toPromise(qryResource.update(query));
                } else {
                    return toPromise(qryResource.save(query));
                }
            },
            deleteQuery:function(qid){
                return qryResource.delete(qid);
            },
            saveUser:function(user){
                return toPromise(user.id?usrResource.update(user):usrResource.save(user));
            },
            getUsers:function(){
                return usrResource.query();
            },
            shareDataSources:function(dsIds, userIds){
                return toPromise($http.post('/share/datasource', {datasources:dsIds, users:userIds}));
            },
            shareQueries:function(qryIds, userIds){
                
            }

        };
    });
