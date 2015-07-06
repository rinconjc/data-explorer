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
                $scope.addTable = function(item){
                    $scope.fromTables.push(item);
                    $scope.model.table = '';
                    var tableMeta = DataService.getTableInfo($scope.dsId, item);
                    $scope.availableColumns
                };
                $scope.delTable = function(index){
                    $scope.fromTables.splice(i,1);
                };
            }
        };
    })
;
