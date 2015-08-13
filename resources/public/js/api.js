angular.module('data-api',['ngResource'])
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
            if(isArray){
                value.$append = function(otherFuture){
                    otherFuture.then(function(){
                        angular.forEach(otherFuture, value.push);
                    });
                };
            }
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
                value.$resolved=true;
            }).error(function(err){
                value.$resolved=true;
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
            getDataTypes: function(ds){
                return futureValue($http.get('/ds/'+ds+'/data-types'), true);
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
                return futureValue($http.post('/ds/' + ds +'/exec-query', {
                    tables:[name], fields:['*'], offset:offset, limit:maxrows
                }), false);
            },
            getTableInfo:function(ds, table){
                return getDsResource(ds, 'tables').get({id:table});
            },
            executeSql:function(ds,sql, offset, limit){
                var params = offset && limit? {offset:offset, limit:limit} : {};
                return futureValue($http.post('/ds/'+ds+'/exec-sql', {
                    "raw-sql":sql}, {params:params}));
            },
            executeQuery:function(tables, fields, conditions, offset, maxrows){
                return futureValue($http.post('/ds/' + ds + '/exec-query', {
                    tables:tables, fields:fields
                    ,predicates:conditions, offset:offset, limit:maxrows}));
            },
            getQueries:function(ds){
                return futureValue($http.get('/ds/'+ds+'/queries'), true);
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
            getQueryAssocs:function(qid){
                return futureValue($http.get('/queries/'+qid+'/data-source'), true);
            },
            assocQuery:function(qid, dsid){
                return futureValue($http.put('/queries/'+qid+'/data-source/'
                                             + dsid));
            },
            dissocQuery:function(qid, dsid){
                return futureValue($http.delete('/queries/'+qid+'/data-source/'
                                             + dsid));
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
            },
            importData:function(dsId, params){
                return futureValue($http.post('/ds/' + dsId + '/import-data', params));
            }
        };
    });
