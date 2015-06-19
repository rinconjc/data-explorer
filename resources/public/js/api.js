angular.module('dbquery.api',['ngResource'])
    .factory('DataService', function($resource, $http, $q){
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

        function futureValue(r, isArray){
            var value =isArray?[]:{},
                d=$q.defer();
            value.$isready=false;
            value.$promise = d.promise;
            r.success(function(res){
                if(isArray){
                    angular.forEach(res, function(item){
                        value.push(item);
                    });
                } else{
                    angular.forEach(res, function(item, key){
                        value[key]=item;
                    });
                }
                d.resolve(value);
                value.$isready=true;
            }).error(function(err){
                value.$isready=true;
                value.$error = err;
                d.reject(err);
            });

            return value;
        }

        function getDsResource(ds, res){
            return getResource(ds+res, '/ds/'+ds+'/'+ res +'/:id');
        }
        var dsResource = getResource('data-source','/data-sources/:id');
        var qryResource = getResource('queries', '/queries/:id');
        var usrResource = getResource('users','/users/:id');

        return {
            getDatasources: function(){
                return dsResource.query();
            },
            saveDatasource:function(ds){
                if(ds.id)
                    return dsResource.update({id:ds.id}, ds).$promise;
                else
                    return dsResource.save(ds).$promise;
            },
            deleteDatasource:function(dsId){
                return dsResource.delete({id:dsId});
            },
            getDataSource:function(id){
                return dsResource.get({id:id});                
            },
            getTables:function(ds){
                return getDsResource(ds, 'tables').query();
            },
            getViews:function(ds){
                return getDsResource(ds, 'views').query();
            },
            getTableData:function(ds, name, offset, maxrows){
                return futureValue($http.post('/ds/' + ds +'/exec-query', {tables:[name], fields:['*'], offset:offset, limit:maxrows}), false);
            },
            getTableInfo:function(ds, table){
                return getDsResource(ds, 'tables').get({id:table});
            },
            executeSql:function(ds,sql){
                return futureValue($http.post('/ds/'+ds+'/exec-sql', {"raw-sql":sql}));
            },
            executeQuery:function(tables, fields, conditions, offset, maxrows){
                return futureValue($http.post('/ds/' + ds + '/exec-query', {tables:tables, fields:fields,
                                                                            predicates:conditions, offset:offset, limit:maxrows}));
            },
            getQueries:function(ds){
                return qryResource.query();
            },
            saveQuery:function(query){
                if(query.id){
                    return qryResource.update({id:query.id},query);
                } else {
                    return qryResource.save(query);
                }
            },
            getQuery:function(id){
                return qryResource.get({id:id});
            },
            deleteQuery:function(qid){
                return qryResource.delete({id:qid});
            },
            execSavedQuery:function(ds, qid){
                return futureValue($http.post('/ds/' + ds + '/exec-query/' + qid));
            },
            saveUser:function(user){
                return user.id?usrResource.update({id:user.id},user):usrResource.save(user);
            },
            getUsers:function(){
                return usrResource.query();
            },
            getCurrentUser:function(){
                return toPromise($http.get('/user'));
            },
            shareDataSources:function(dsIds, userIds){
                return toPromise($http.post('/share/datasource', {datasources:dsIds, users:userIds}));
            },
            shareQueries:function(qryIds, userIds){
                return toPromise($http.post('/share/query', {queries:qryIds, users:userIds}));
            }
        };
    });
