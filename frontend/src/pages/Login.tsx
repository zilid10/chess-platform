import { useActionState } from 'react';
import { apiErrorMessage } from '../services/errors';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const initialState = { error: '', values: { username: '', password: '' } };

const Login = () => {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  if (isAuthenticated) {
    navigate('/dashboard');
  }

  // React resets the form after each submit, so the typed values come back as its defaults.
  const signIn = async (_prev: typeof initialState, formData: FormData) => {
    const values = Object.fromEntries(formData) as typeof initialState.values;
    try {
      await login(values.username, values.password);
      navigate('/dashboard');
      return { error: '', values };
    } catch (err) {
      return { error: apiErrorMessage(err, 'Login failed. Please check your credentials.'), values };
    }
  };
  const [state, formAction, loading] = useActionState(signIn, initialState);

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div>
          <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
            Sign in to Chess Platform
          </h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Or{' '}
            <Link to="/register" className="font-medium text-primary-600 hover:text-primary-500">
              create a new account
            </Link>
          </p>
        </div>
        <form className="mt-8 space-y-6" action={formAction}>
          {!loading && state.error && (
            <div className="rounded-md bg-red-50 p-4">
              <p className="text-sm text-red-800">{state.error}</p>
            </div>
          )}
          <div className="rounded-md shadow-xs -space-y-px">
            <div>
              <label htmlFor="username" className="sr-only">
                Username
              </label>
              <input
                id="username"
                name="username"
                type="text"
                required
                className="input rounded-t-md rounded-b-none"
                placeholder="Username"
                defaultValue={state.values.username}
              />
            </div>
            <div>
              <label htmlFor="password" className="sr-only">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                required
                className="input rounded-b-md rounded-t-none"
                placeholder="Password"
                defaultValue={state.values.password}
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={loading}
              className="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700 focus:outline-hidden focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50"
            >
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Login;
