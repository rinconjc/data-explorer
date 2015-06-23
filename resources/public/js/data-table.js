angular.module('data-table',[])
    .directive('ngTable',function(){
        return {
            scope:{
                columns:'=',
                data:'=',
                class:'@'
            },
            replace:true,
            transclude:true,
            template:'<table class="table {{class}}" data-len="{{columns.length}}"><thead><tr><th ng-repeat="col in columns">{{col}}</th></tr></thead><tbody><tr ng-repeat="row in data"><td ng-repeat="item in row track by $index" title="{{item}}">{{item}}</td></tr></tbody></table>',
            controller:function($scope){
                
            }
        };
    });
