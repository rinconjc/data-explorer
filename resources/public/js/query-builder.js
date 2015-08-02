angular.module('query-builder',[])
    .constant('CriteriaConsts', {
        operators:['=','!=','is','<=','>=','>','<','in', 'like','between'],
        operandTypes:['value','expression','column']
    })
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
            controller:function($scope, DataService, CriteriaConsts){
                $scope.CriteriaConsts=CriteriaConsts;
                $scope.model = {};
                $scope.fromTables=[];
                $scope.columns=[];
                $scope.tables = DataService.getTables($scope.dsId);
                $scope.relatedTables={};
                $scope.criteria=[];
                $scope.criterion={operandType:'Value'};
                $scope.addTable = function(item){
                    $scope.fromTables.push(item);
                    removeElem($scope.tables, item);
                    $scope.model.table = '';
                    var tableMeta = DataService.getTableInfo($scope.dsId, item);
                    tableMeta.$promise.then(function(){
                        $scope.columns.push({table:item, name:'*', special:true, selected:false});
                        angular.forEach(tableMeta.columns, function(r){
                            $scope.columns.push({table:item, name:r.column_name, special:false, selected:false});
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
                    var tableCols = function(item){
                        return item.table==item;
                    };
                    $scope.columns = _.filter($scope.columns, tableCols);
                };
                $scope.addRelated = function(rt){
                    $scope.addTable(rt);
                    delete $scope.relatedTables[rt];
                };
            }
        };
    })
;
