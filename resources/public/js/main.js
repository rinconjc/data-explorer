angular.module('dbquery', ['ngResource', 'ngRoute', 'ui.bootstrap', 'data-table','common-widgets', 'dbquery.api'])
    .constant('CONSTS', {
        EVENTS:{
            DS_ADDED:'ds-added',
            DS_CHANGED:'ds-changed'
        }
    })
    .config(function($routeProvider){
        $routeProvider
            .when('/', {templateUrl:'tpls/dashboard.html'})
            .when('/login', {template:'<login-form login-fn="doLogin" heading="Login to DataExplorer" alert-text="alert" allow-remember="true"/>', controller:'LoginCtrl'})
            .when('/connect', {templateUrl:'tpls/db-connect.html', controller:'DataSourceCtrl'})
            .when('/dash/:db', {templateUrl:'tpls/db-dash.html', controller:'DBCtrl'})
            .otherwise({
                redirectTo:'/connect'
            })
        ;
    })
    .factory('DataSource', function($resource){
        return $resource('/data-source/:id');
    })
    .directive('tableList', function(){
        return {
            scope:{
                heading:'@',
                rows:'@',
                resource:'='
            },
            controller:function($scope){
                $scope.refresh = function(){
                    console.debug('refreshing data');
                    $scope.data = $scope.resource.query();
                };
                $scope.refresh();
                $scope._btnClick=function(btn, selected){
                    console.debug('showing data...', selected, $scope.doShowData);
                    $scope.$emit('EVT.TABLES.' + btn, selected);
                };
            },
            templateUrl:'tpls/table-list.html'
        };
    })
    .controller('LoginCtrl', function($scope, $http, $rootScope, $location){
        $scope.doLogin = function(loginData){
            console.debug('logging with ', loginData);
            $http.post('/login', loginData).success(function(user){
                $rootScope.user=user;
                $location.path('/');
                $scope.refreshDatasources();
            }).error(function(err){
                $scope.alert=err;
            });
        };
    })
    .controller('MainCtrl', function($scope, $rootScope, $location, $http, $q, DataSource, CONSTS, $routeParams){
        function isLoggedIn(){
            if($rootScope.user)
                return $q.when($rootScope.user);
            return $http.get('/user').then(function(res){
                return res.data;
            });
        }
        $scope.refreshDatasources = function(){
            console.debug('refreshing datasources...');
            $rootScope.datasources = DataSource.query();
        };
        isLoggedIn().then(function(user){
            $rootScope.user = user;
            $scope.refreshDatasources();
        },function(e){
            $location.path('/login');
        });
        $scope.main={};
        $scope.dsChanged = function(){
            console.debug('ds changed:', $scope.main.curds, ds1, "equal?", ds1===$scope.main.curds);
            $location.path('/dash/'  + $scope.main.curds);
        };
        $rootScope.$on(CONSTS.EVENTS.DS_ADDED, function(evt,args){
            console.debug('ds updated...reloading');
            $scope.refreshDatasources();
            $scope.main.curds = args.id;
            $scope.dsChanged();
        });
        $scope.$on(CONSTS.EVENTS.DS_CHANGED, function(evt,args){
            console.debug('ds changed...', args);
            $scope.main.curds=args;
        });
        $scope.$watch('curds', function(newVal,oldVal){
            console.debug('watch:', oldVal, newVal);            
        });

    })
    .controller('DataSourceCtrl', function($scope, $http, $log, $location, DataSource, CONSTS){
        $scope.dbcon = {};
        $scope.save = function(){
            $scope.waiting = true;
            DataSource.save($scope.dbcon, function(res){
                $log.debug('datasource created ', res);
                $scope.$emit(CONSTS.EVENTS.DS_ADDED, $scope.dbcon);
            }, function(err){
                $log.error('failed creating datasource', err);
                $scope.message = err.data;
                $scope.waiting =false;
            });
        };
    })
    .controller('DBCtrl', function($scope, $rootScope, $resource, $log, $http, $routeParams, CONSTS){
        $scope.selected = null;
        $scope.sql = {};
        $scope.tableTabs={};
        var ctx = '/ds/'+ $routeParams.db;
        $scope.$emit(CONSTS.EVENTS.DS_CHANGED, parseInt($routeParams.db));
        
        $scope.tables = $resource(ctx+'/tables/:name', {name:'@selected'}, {content:{method:'GET', isArray:true}});
        $scope.$on('EVT.TABLES.DATA', function(evt, args){
            console.debug('tables data evt received..', args);
            angular.forEach(args, function(tbl){
                if(!$scope.tableTabs[tbl]){
                    $scope.tableTabs[tbl]=$scope.tables.get({name:tbl});
                }
            });                        
        });
        
        $scope.execute = function(){
            $scope.error = null;
            $http.post(ctx+'/execute', {"raw-sql":$scope.sql.text}).success(function(res){
                if(typeof res.rowsAffected != 'undefined') {
                    $scope.rowsAffected = 'Rows affected ' + res.rowsAffected;
                    $scope.resultData = null;
                } else {
                    $scope.rowsAffected = null;
                    $scope.resultData = res;
                }
            }).error(function(err){
                $log.debug('failed executing query', err);
                $scope.error = err;
            });
        };
    });
