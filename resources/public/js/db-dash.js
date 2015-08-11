angular.module('db-dash',['data-api', 'ui.codemirror', 'ui.bootstrap','cfp.hotkeys','common-widgets', 'query-builder'])
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
                previewClicked:'=',
                active:'='
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
                $scope.$watch('active', function(newVal, oldVal){
                    if(!newVal) return;
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
                });
            },
            templateUrl:'tpls/table-list.html'
        };
    })
    .directive('dbConsole', function(){
        return {
            scope:{
                ds:'=',
                active:'='
            },
            templateUrl:'tpls/db-dash.html',
            controller: function($scope, CONSTS, DataService, $modal, hotkeys, focus, preventDefault){
                var pageSize = 20,
                    dsId = $scope.ds.id; //$routeParams.db;
                $scope.dsId=dsId;
                $scope.previewTabs={};
                $scope.infoTabs={};
                $scope.tabSwitch={SQL:true};

                $scope.showTableInfo = function(selection){
                    angular.forEach(selection, function(tbl){
                        if(!$scope.infoTabs[tbl]){
                            $scope.infoTabs[tbl]=DataService.getTableInfo(dsId, tbl);
                            $scope.tabSwitch['INFO-'+tbl]=true;
                        }
                    });
                };
                $scope.showTablePreview =function(selection){
                    angular.forEach(selection, function(tbl){
                        if(!$scope.previewTabs[tbl]){
                            $scope.previewTabs[tbl]=DataService.getTableData(dsId, tbl, 0, 20);
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
                $scope.fetchNext = function(tbl){
                    var rows = $scope.previewTabs[tbl].rows;
                    var next = DataService.getTableData(dsId, tbl, rows.length, 20);
                    next.$promise.then(function(){
                        angular.forEach(next.rows, function(row){
                            rows.push(row);
                        });
                    });
                };
            }
        };
    })
    .directive('sqlPanel', function(DataService, $modal){
        var pageSize=20;
        return {
            scope:{
                dsId:'=',
                active:'='
            },
            templateUrl:'tpls/sql-panel.html',
            controller:function($scope,$rootScope, hotkeys, focus, preventDefault){
                var dsId = $scope.dsId;
                $scope.datasources = $rootScope.datasources;
                $scope.query = {};
                $scope.model = {selection:[]};
                $scope.queries = DataService.getQueries(dsId);
                $scope.results = [];
                $scope.activeTab = {};
                var uid=0;
                $scope.execute = function(){
                    var sql = $scope.editor.getSelection() || $scope.query.sql;
                    var resp = DataService.executeSql(dsId, sql);

                    $scope.result=resp;
                    resp.$promise.then(function(){
                        if(resp.data){
                            var id = uid++;
                            $scope.results.push({sql:sql, data:resp.data, id:id, hasMore:(resp.data.rows.length>=pageSize)});
                            $scope.activeTab[id]=true;
                        }
                    });
                };
                $scope.closeResult = function(index){
                    $scope.results.splice(index,1);
                };

                $scope.fetchNext = function(i){
                    var res = $scope.results[i],
                        rows = res.data.rows,
                        next = DataService.executeSql(dsId, $scope.results[i].sql, rows.length, pageSize);
                    next.$promise.then(function(){
                        angular.forEach(next.data.rows, function(row){
                            rows.push(row);
                        });
                        res.hasMore = next.data.rows.length>=pageSize;
                    });
                };

                $scope.$watch('active', function(newVal, oldVal){
                    console.debug('console active state changed:', newVal,oldVal);
                    if(newVal){
                        hotkeys.bindTo($scope)
                            .add({combo:'ctrl+e', callback:preventDefault($scope.execute), allowIn: ['INPUT', 'SELECT', 'TEXTAREA']})
                            .add({combo:'ctrl+l', callback:preventDefault(function(){$scope.clear(); focus('enterSql');}), allowIn: ['INPUT', 'SELECT', 'TEXTAREA']})
                            .add({combo:'alt+f', callback:preventDefault(focus, 'searchQuery')})
                        ;
                    }
                });

                $scope.editorLoaded = function(_editor){
                    console.debug('editor loaded...', _editor);
                    $scope.editor = _editor;
                    _editor.focus();
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
                                };
                            }
                        }).result.then(function(saved){
                            console.debug('saved!', saved);
                            angular.extend(saved, $scope.query);
                        });
                    }
                };
                $scope.loadQuery = function(item, model, label){
                    $scope.query = DataService.getQuery(item.id);
                };
                $scope.clear = function(){
                    $scope.query = {};
                };
                $scope.refresh = function(r){
                    var resp = DataService.executeSql(dsId, r.sql);
                    r.data.rows=[];
                    resp.$promise.then(function(){
                        r.data = resp.data;
                        r.hasMore = resp.data.rows.length>=pageSize;
                    });
                };
            }
        };
    })
;
