angular.module('dbquery', ['ngResource', 'ngRoute'])
    .config(function($routeProvider){
        $routeProvider
            .when('/', {templateUrl:'parts/db-connect.html', controller:'DataSourceCtrl'})
            .when('/:db', {templateUrl:'parts/db-dash.html', controller:'DBCtrl'})
            .otherwise({
                redirectTo:'/'
            })
        ;

    })
    .controller('DataSourceCtrl', function($scope, $http, $log, $location){
        $scope.dbcon = {};
        $scope.save = function(){
            $scope.waiting = true;
            $http.post('/data-source', $scope.dbcon).success(function(res){
                $log.debug('datasource created ', res);
                $location.path('/default');
            }).error(function(err){
                $log.error('failed creating datasource', err);
                $scope.message = err;
                $scope.waiting =false;
            });
        };
    })
    .controller('DBCtrl', function($scope, $resource, $log){
        $scope.selected = null;
        var Tables = $resource('/tables/:name', {name:'@selected'}, {content:{method:'GET', isArray:true}});
        $scope.tables = Tables.query();
        $scope.tableSelected = function(table){
            $log.debug('table', table, 'has been selected');            
            $scope.data = Tables.get({name:table});
        };
    });
