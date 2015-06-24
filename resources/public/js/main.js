angular.module('dbquery', ['ngResource', 'ngRoute', 'ui.bootstrap', 'common-widgets', 'dbquery.api','db.dash'])
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
            .when('/data-source/:id?', {templateUrl:'tpls/db-connect.html', controller:'DataSourceCtrl'})
            .when('/dash/:db', {templateUrl:'tpls/db-dash.html', controller:'DBCtrl'})
            .otherwise({
                redirectTo:'/data-source'
            })
        ;
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
    .controller('MainCtrl', function($scope, $rootScope, $location, $http, $q, DataService, CONSTS, $routeParams){
        function isLoggedIn(){
            if($rootScope.user)
                return $q.when($rootScope.user);
            return $http.get('/user').then(function(res){
                return res.data;
            });
        }
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
;
