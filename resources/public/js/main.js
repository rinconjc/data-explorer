angular.module('main', ['ngResource', 'ngRoute', 'ui.bootstrap', 'common-widgets', 'data-api','db-dash', 'query-builder','cfp.hotkeys','data-import'])
    .constant('CONSTS', {
        EVENTS:{
            DS_ADDED:'ds-added',
            DS_CHANGED:'ds-changed',
            OPEN_DB:'open-db'
        }
    })
    .config(function($routeProvider){
        $routeProvider
            .when('/', {templateUrl:'tpls/dashboard.html'})
            .when('/login', {template:'<login-form login-fn="doLogin" heading="Login to DataExplorer" alert-text="alert" allow-remember="true"/>', controller:'LoginCtrl'})
            .when('/data-source/:id?', {templateUrl:'tpls/db-connect.html', controller:'DataSourceCtrl'})
        //.when('/dash/:db', {templateUrl:'tpls/db-dash.html', controller:'DBCtrl'})
            .when('/build-query', {templateUrl:'tpls/query-builder.html', controller:'QueryBuilderCtrl'})
            .when('/db-tabs', {templateUrl:'tpls/db-tabs.html', controller:'DbTabsCtrl'})
            .otherwise({
                redirectTo:'/data-source'
            })
        ;
    })
    .controller('LoginCtrl', function($scope, $http, $rootScope, $location){
        $scope.doLogin = function(loginData){
            $http.post('/login', loginData).success(function(user){
                $rootScope.user=user;
                //load user modules definitions
                $location.path('/');
                $scope.refreshDatasources();
            }).error(function(err){
                $scope.alert=err;
            });
        };
    })
    .controller('MainCtrl', function($scope, $rootScope, $location, $http, $q, DataService, CONSTS, $routeParams, $modal, $timeout, hotkeys){
        function isLoggedIn(){
            if($rootScope.user)
                return $q.when($rootScope.user);
            return $http.get('/user').then(function(res){
                return res.data;
            });
        }
        function openDb(){
            if($location.path()!='/db-tabs'){
                $location.path('/db-tabs');
            }
            $modal.open({
                templateUrl:'tpls/select-db.html',
                size:'sm',
                resolve:{
                    title:function(){return 'Open Database';},
                    items:function(){return $rootScope.datasources;}
                },
                controller:function($scope, $modalInstance, title, items){
                    $scope.title=title;
                    $scope.items=items;
                    $scope.ok = function(selected){
                        $modalInstance.close(selected);
                    };
                    $scope.cancel = function(){
                        $modalInstance.dismiss();
                    }
                }
            }).result.then(function(db){
                console.debug('db selected:', db,'location:', $location.path());
                $timeout(function(){
                    $scope.$emit(CONSTS.EVENTS.OPEN_DB, db);
                })
            });
        }

        hotkeys.bindTo($scope).add({combo:'alt+o', callback:openDb});

        $scope.menuBar = [
            {label:'Home', href:'#/'},
            {label:'Open DB', action:openDb},
            {label:'Add Connection', href:'#/data-source'},
            {label:'Data', items:[
                {label:'Import', event:'data.import'},
                {label:'Export', event:'data.export'},
            ]}
        ];
        $scope.refreshDatasources = function(){
            console.debug('refreshing datasources...');
            $rootScope.datasources = DataService.getDatasources();
        };
        isLoggedIn().then(function(user){
            $rootScope.user = user;
            $scope.refreshDatasources();
        },function(e){
            $location.path('/login');
        });
        $scope.main={};
        $scope.dsChanged = function(){
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
        $scope.deleteDs = function(id){
            DataService.deleteDatasource(id).$promise.then(function(){
                $scope.refreshDatasources();
            }, function(err){
                console.log('failed deleting...', err);
            });
        };
        $scope.edit = function(id){
            $location.path('/data-source/' + id);
        };
    })
    .controller('DataSourceCtrl', function($scope, $http, $log, $location, $routeParams, DataService, CONSTS){
        var id = $routeParams.id;
        $scope.dbcon = id?DataService.getDataSource(id):{};
        $scope.save = function(){
            $scope.waiting = true;
            DataService.saveDatasource($scope.dbcon).then(function(res){
                $log.debug('datasource created ', res);
                $scope.$emit(CONSTS.EVENTS.DS_ADDED, res);
            }, function(err){
                $log.error('failed creating datasource', err);
                $scope.message = err.data;
                $scope.waiting =false;
            });
        };
    })
    .controller('DbTabsCtrl', function($scope, CONSTS, $rootScope){
        $scope.activeState={};
        $scope.openDbs=[];
        $rootScope.$on(CONSTS.EVENTS.OPEN_DB, function(evt, db){
            if($scope.openDbs.indexOf(db)<0){
                console.debug('opendb', db);
                $scope.openDbs.push(db);
                $scope.activeState[db.id]=true;
            }
        });
        $scope.closeTab = function(index){
            console.debug('closing:', index);
            $scope.openDbs.splice(index,1);
            console.debug('open:', $scope.openDbs);
        };
    })
;
