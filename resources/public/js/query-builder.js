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
                $scope.tables = DataService.getTables($scope.dsId);
                $scope.addTable = function(item){
                    $scope.fromTables.push(item);
                    $scope.model.table = '';
                };
                $scope.delTable = function(index){
                    $scope.fromTables.splice(i,1);
                };
            }
        };
    })
;
