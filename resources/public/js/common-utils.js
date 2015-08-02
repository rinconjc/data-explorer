angular.module('common-utils', [])
    .factory('CommonUtils', function($q, $http){

        function arrayToObject(arr, obj, keyProp, valProp){
            if(keyProp && valProp){
                angular.forEach(arr, function(elem){
                    obj[elem[keyProp]]= elem[valProp];
                });
            } else {
                angular.forEach(arr, function(elem){
                    obj[elem]= elem;
                });
            }
            return obj;
        }

        return {
            toPromise:function(r){
                var q =$q.defer();
                r.success(function(res){
                    q.resolve(res);
                }).error(function(err){
                    q.reject(err);
                });
                return q.promise;
            },
            futureValue:function (r, isArray){
                var value =isArray?[]:{},
                    d=$q.defer();
                value.$isready=false;
                value.$promise = d.promise;
                if(isArray){
                    value.$append = function(otherFuture){
                        otherFuture.then(function(){
                            angular.forEach(otherFuture, value.push);
                        });
                    }
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
            },
            postFormData: function(url, data){
                var fd = new FormData();
                angular.forEach(data, function(v,k){
                    fd.append(k, v);
                });
                return this.futureValue($http.post(url, fd, {
                    transformRequest: angular.identity,
                    headers: {'Content-Type': undefined}
                }));
            },
            toObject:function(arr, keyProp, valProp){
                if(arr.$promise && !arr.$resolved){
                    var obj = {};
                    arr.$promise.then(function(){
                        arrayToObject(arr, obj, keyProp, valProp);
                    });
                    return obj;
                }
                return arrayToObject(arr, {}, keyProp, valProp);
            }
        };
    });
