angular.module('db.dash',['dbquery.api', 'ui.codemirror', 'ui.bootstrap','cfp.hotkeys','common-widgets'])
    .constant('preventDefault', function(f){
        var rest = Array.prototype.slice.call(arguments).slice(1);
        return function(evt){
            evt.preventDefault();
            return f.apply(arguments.caller, rest);
        };
    })
    .directive('tableList', function(DataService, hotkeys, focus, preventDefault){
        return {
            scope:{
                heading:'@',
                rows:'@',
                ds:'=',
                infoClicked:'=',
                previewClicked:'='
            },
            controller:function($scope){
                $scope.model={items:[]};
                $scope.refresh = function(){
                    $scope.tables = DataService.getTables($scope.ds);
                    $scope.views = DataService.getViews($scope.ds);
                };
                $scope.switchSearch = function(onOff){
                    $scope.model.searchActive=onOff;
                    if(onOff)
                        focus('tableSearchActivated');
                    else
                        $scope.model.filterValue='';
                };
                $scope.refresh();
                hotkeys.bindTo($scope)
                    .add({combo:'/', allowIn:['SELECT'] ,
                          callback:preventDefault($scope.switchSearch, true)})
                    .add({combo:'esc',allowIn:['INPUT','SELECT'],
                          callback:preventDefault($scope.switchSearch, false)})
                    .add({combo:'ctrl+i',allowIn:['SELECT'],
                          callback:preventDefault(function(){$scope.infoClicked($scope.model.items);})})
                    .add({combo:'ctrl+d',allowIn:['SELECT'],
                          callback:preventDefault(function(){$scope.previewClicked($scope.model.items);})})
                ;
            },
            templateUrl:'tpls/table-list.html'
        };
    })
    .controller('DBCtrl', function($scope, $rootScope, $log, $routeParams, CONSTS, DataService, $modal, hotkeys, focus, preventDefault){
        $scope.query = {};
        $scope.previewTabs={};
        $scope.infoTabs={};
        $scope.model = {selection:[]};
        $scope.dsId=$routeParams.db;
        $scope.$emit(CONSTS.EVENTS.DS_CHANGED, parseInt($routeParams.db));
        $scope.queries = DataService.getQueries($scope.dsId);
        $scope.tabSwitch={SQL:true};

        $scope.showTableInfo = function(selection){
            console.debug('showing table info for: ', selection);
            angular.forEach(selection, function(tbl){
                if(!$scope.infoTabs[tbl]){
                    $scope.infoTabs[tbl]=DataService.getTableInfo($scope.dsId, tbl);
                    $scope.tabSwitch['INFO-'+tbl]=true;
                }
            });
        };
        $scope.showTablePreview =function(selection){
            console.debug('showing table preview for :', selection);
            angular.forEach(selection, function(tbl){
                if(!$scope.previewTabs[tbl]){
                    $scope.previewTabs[tbl]=DataService.getTableData($scope.dsId, tbl, 0, 50);
                    $scope.tabSwitch[tbl]=true;
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
                    angular.merge(saved, $scope.query);
                });
            }
        };
        $scope.loadQuery = function(item, model, label){
            $scope.query = DataService.getQuery(item.id);
        };
        $scope.clear = function(){
            $scope.query = {};
        };
        hotkeys.bindTo($scope)
            .add({combo:'ctrl+e', callback:preventDefault($scope.execute), allowIn: ['INPUT', 'SELECT', 'TEXTAREA']})
            .add({combo:'ctrl+l', callback:preventDefault(function(){$scope.clear(); focus('enterSql');}), allowIn: ['INPUT', 'SELECT', 'TEXTAREA']})
            .add({combo:'alt+f', callback:preventDefault(focus, 'searchQuery')})
        ;
    });
