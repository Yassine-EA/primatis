import { routes } from './app.routes';

describe('Application routes', () => {
  it('should expose the expected application zones', () => {
    const paths = routes.map((route) => route.path);

    expect(paths).toContain('');
    expect(paths).toContain('member');
    expect(paths).toContain('staff');
    expect(paths).toContain('admin');
    expect(paths).toContain('forbidden');
    expect(paths).toContain('**');
  });

  it('should keep the wildcard route last', () => {
    expect(routes.at(-1)?.path).toBe('**');
  });
});
