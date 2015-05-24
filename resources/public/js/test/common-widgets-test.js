describe('common-widgets-directives', function(){
    var element, scope, element2;

    beforeEach(function(){
        module('my.templates');        
        module('common-widgets');        
    });

    beforeEach(inject(function($rootScope, $compile, $injector, $log) {
        scope = $rootScope.$new();
        scope.doLogin = function(userData){
            console.debug('authenticating user ', userData);
            return true;
        };
        element = $compile('<login-form use-email="true" login-fn="doLogin" />')(scope);
        element2 = $compile('<login-form login-fn="doLogin" heading="Login Title"/>')(scope);
        scope.$digest();
    }));

    describe('login-form', function(){        
        it('should render form', function(){
            var isolated = element.isolateScope();
            console.debug('elem html', element.html());
            expect(isolated.useEmail).toBe('true');
            expect(element2.find('h2').text()).toBe('Login Title');
        });
        
        it('should handle click', function(){
            var btn = element.find('button'),
                scope1 = element.isolateScope();
            element.find('#inputUsername').val('test-user');
            scope.$digest();            
            $(btn).trigger('click');
            expect(scope1.clicked).toBe(true);
        });
    });
    

});
