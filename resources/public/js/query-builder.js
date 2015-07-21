angular.module('query.builder',[])
    .directive('queryBuilder', function(){
        function removeElem(arr, elem){
            var i = arr.indexOf(elem);
            if(i>=0){
                arr.splice(i,1);
            }
        }
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
                $scope.relatedTables={};
                $scope.selectedColumns=[];
                $scope.addTable = function(item){
                    $scope.fromTables.push(item);
                    removeElem($scope.tables, item);
                    $scope.model.table = '';
                    var tableMeta = DataService.getTableInfo($scope.dsId, item);
                    tableMeta.$promise.then(function(){
                        $scope.availableColumns.push(item+'.*');
                        angular.forEach(tableMeta.columns, function(r){
                            $scope.availableColumns.push(item + '.' + r.column_name);
                        });
                        angular.forEach(tableMeta.foreignKeys, function(v,k){
                            $scope.relatedTables[v.pktable_name]=1;
                        });
                    });
                };
                $scope.delTable = function(index){
                    var tbl = $scope.fromTables[index];
                    $scope.tables.push(tbl);
                    $scope.fromTables.splice(index,1);
                    $scope.availableColumns = _.filter($scope.availableColumns, function(item){
                        return !item.startsWith(tbl+'.');
                    });
                };
                $scope.addRelated = function(rt){
                    $scope.addTable(rt);
                    delete $scope.relatedTables[rt];
                };
                $scope.addColumn = function(col){
                    removeElem($scope.availableColumns, col);
                    $scope.selectedColumns.push(col);
                };
                $scope.moveCol = function(col, fromArray, toArray){
                    removeElem(fromArray, col);
                    toArray.push(col);
                };
            }
        };
    })
;
