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
                $scope.refresh = function(resync){
                    $scope.tables = DataService.getTables($scope.ds, resync);
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
    .factory('QueryBuilder', function(DataService, $filter){
        return function(dsId){
            var tables =DataService.getTables(dsId);
            var prefixes = {
                'f': {
                    options:function (q) {
                        return tables; // exclude q.from?
                    },
                    selected:function(q, input){
                        q.from.push(input);
                        if(!q._forJoin) q._forJoin=[];
                        q._forJoin.push(input);
                    }
                },
                'j': {
                    options: function (q, input) {
                        if(!q.from || q.from.length<=0) return [];
                        if(!q._forJoin || q._forJoin.length==0)
                            return _.keys(q._joinOptions);
                        var related = DataService.getRelatedTables(dsId, q._forJoin);
                        if(!q._joinOptions) q._joinOptions={};
                        var items = _.keys(q._joinOptions);
                        related.$promise.then(function(){
                            q._joinOptions = _.extend(related,q._joinOptions);
                        });
                        q._forJoin=[];
                        return _.keys(q._joinOptions);
                    },
                    selected:function(q, input){
                        var joinT = q._joinOptions[input];
                        q.joins.push(joinT);
                        q._forJoin.push(input);
                    }
                },
                's':{
                    options: function (q, input) {
                        if(q._selectOptions) return q._selectOptions;
                        if(!q.from || q.from.length<=0) return [];
                        q._selectOptions = [];//... union of all from tables - selected columns
                        return q._selectOptions;
                    }
                }
            };
            return {
                from:[],
                columns:[],
                joins:[],
                suggestions:function(input){
                    var prefNtext = input.split(':'),
                        items=[];
                    if(prefNtext[0]) {
                        items=prefixes[prefNtext[0]].(this);
                        items=prefNtext[1]?$filter('filter')(items, prefNtext[1]) : items;
                        this._prefix=prefNtext[0];
                    }
                    console.debug('type ahead:', input, items);
                    return items;
                },
                update:function(input){

                }
            };
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
            controller:function($scope,$rootScope,$filter, hotkeys, focus, preventDefault){
                var dsId = $scope.dsId;
                $scope.query = {};
                $scope.model = {selection:[]};
                $scope.queries = DataService.getQueries(dsId);
                $scope.results = [];
                $scope.activeTab = {};
                var uid=0;
                var prefixes = {'f':['Fox', 'Wolf', 'Horse', 'Hunter'],
                                'lf':['Water', 'Earth','Fire']};

                $scope.smartQueryItems = function(input){
                    var prefNtext = input.split(':'),
                        items=[];
                    if(prefNtext[0]) {
                        items=prefixes[prefNtext[0]];
                        items=prefNtext[1]?$filter('filter')(items, prefNtext[1]) : items;
                    }
                    console.debug('type ahead:', input, items);
                    return items;
                };
                $scope.updateQuery = function(entry){
                    var prefNtext = input.split(':');

                };
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
                                query:function(){return $scope.query;}
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
                            DataService.assocQuery(saved.id, dsId);
                        });
                    }
                };
                $scope.loadQuery = function(item, model, label){
                    $scope.query = DataService.getQuery(item.id);
                    $scope.assocs = null;
                };
                $scope.clear = function(){
                    $scope.query = {};
                    $scope.assocs = null;
                };
                $scope.deleteQuery = function(){
                    if($scope.query.id){
                        DataService.deleteQuery($scope.query.id);
                        $scope.clear();
                    }
                };

                $scope.refresh = function(r){
                    var resp = DataService.executeSql(dsId, r.sql);
                    r.data.rows=[];
                    resp.$promise.then(function(){
                        r.data = resp.data;
                        r.hasMore = resp.data.rows.length>=pageSize;
                    });
                };
                $scope.loadAssocs = function(open){
                    if(!open || !$scope.query.id || $scope.assocs) return;
                    $scope.assocs = DataService.getQueryAssocs($scope.query.id);
                };
                $scope.flipAssoc = function(assoc){
                    if(assoc.query_id){
                        DataService.dissocQuery($scope.query.id, assoc.id).
                            $promise.then(function(){
                                assoc.query_id=null;
                            });
                    }else{
                        DataService.assocQuery($scope.query.id, assoc.id).
                            $promise.then(function(){
                                assoc.query_id = $scope.query.id;
                            });
                    }
                };
            }
        };
    })
;
