angular.module('db.dash',['dbquery.api', 'ui.codemirror', 'ui.bootstrap'])
    .directive('tableList', function(DataService){
        return {
            scope:{
                heading:'@',
                rows:'@',
                ds:'=',
                infoClicked:'=',
                previewClicked:'='
            },
            controller:function($scope){
                $scope.selection={items:[]};
                $scope.refresh = function(){
                    $scope.tables = DataService.getTables($scope.ds);
                    $scope.views = DataService.getViews($scope.ds);
                };
                $scope.refresh();
            },
            templateUrl:'tpls/table-list.html'
        };
    })
    .controller('DBCtrl', function($scope, $rootScope, $log, $routeParams, CONSTS, DataService, $modal){
        $scope.query = {};
        $scope.previewTabs={};
        $scope.infoTabs={};
        $scope.model = {selection:[]};
        $scope.dsId=$routeParams.db;
        $scope.$emit(CONSTS.EVENTS.DS_CHANGED, parseInt($routeParams.db));
        $scope.showTableInfo = function(selection){
            console.debug('showing table info for: ', selection);
            angular.forEach(selection, function(tbl){
                if(!$scope.infoTabs[tbl]){
                    $scope.infoTabs[tbl]=DataService.getTableInfo($scope.dsId, tbl)
                }
            });
        };
        $scope.showTablePreview =function(selection){
            console.debug('showing table preview for :', selection);
            angular.forEach(selection, function(tbl){
                if(!$scope.previewTabs[tbl]){
                    $scope.previewTabs[tbl]=DataService.getTableData($scope.dsId, tbl, 0, 50);
                }
            });
        };
        $scope.removeTablePreview = function(name){
            delete $scope.previewTabs[name];
        };
        $scope.removeTableInfo = function(name){
            delete $scope.infoTabs[name];
        };
        $scope.execute = function(){
            $scope.result = DataService.executeSql($scope.dsId, $scope.query.sql);
        };
        $scope.saveSql = function(){
            if($scope.query.id){
                DataService.saveQuery($scope.query);
            } else{
                $modal.open({
                    templateUrl:'tpls/query-form.html',
                    resolve:{
                        query:function(){return $scope.query}
                    },
                    controller:function($scope, $modalInstance, query){
                        $scope.query = query;
                        $scope.save = function(){
                            DataService.saveQuery($scope.query).$promise.then(function(saved){
                                $modalInstance.close(saved);
                            }, function(err){
                                $scope.errors = err;
                            });
                        };
                        $scope.cancel = function(){
                            $modalInstance.dismiss('cancel');
                        }
                    }
                }).result.then(function(saved){
                    console.debug('saved!', saved);
                    $scope.query.id = saved.id;
                });
            }

        };
    });
