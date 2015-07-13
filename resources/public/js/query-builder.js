angular.module('query.builder',[])
    .directive('queryBuilder', function(){
        return {
            scope:{
                dsId:'='
            },
            templateUrl:'tpls/query-builder.html',
            controller:function($scope, DataService){
                $scope.model = {};
                $scope.fromTables=[];
                $scope.availableColumns=[];
                $scope.tables = DataService.getTables($scope.dsId);
                $scope.relatedTables=[];
                $scope.addTable = function(item){
                    $scope.fromTables.push(item);
                    $scope.model.table = '';
                    var tableMeta = DataService.getTableInfo($scope.dsId, item);
                    tableMeta.$promise.then(function(){
                        angular.forEach(tableMeta.columns, function(r){
                            $scope.availableColumns.push(r.column_name);
                        });
                        angular.forEach(tableMeta.foreignKeys, function(v,k){
                            $scope.relatedTables.push(v.pktable_name);
                        });
                        console.debug('cols:', $scope.availableColumns);
                    });
                };
                $scope.delTable = function(index){
                    $scope.fromTables.splice(index,1);
                };
            }
        };
    })
;
