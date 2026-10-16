import { useActionState } from 'react';
import { apiErrorMessage } from '../services/errors';
import { useNavigate, Link } from 'react-router-dom';
import { userService } from '../services/userService';
import { useAuth } from '../context/AuthContext';

const initialState = {
  error: '',
  values: { username: '', email: '', password: '', confirmPassword: '', about: '' },
};

const Register = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  if (isAuthenticated) {
    navigate('/dashboard');
  }

  // React resets the form after each submit, so the typed values come back as its defaults.
  const register = async (_prev: typeof initialState, formData: FormData) => {
    const values = Object.fromEntries(formData) as typeof initialState.values;

    if (values.password !== values.confirmPassword) {
      return { error: 'Passwords do not match', values };
    }

    try {
      await userService.createUser({
        username: values.username,
        email: values.email,
        rawPassword: values.password,
        about: values.about || undefined,
      });
      navigate('/login');
      return { error: '', values };
    } catch (err) {
      return { error: apiErrorMessage(err, 'Registration failed. Please try again.'), values };
    }
  };
  const [state, formAction, loading] = useActionState(register, initialState);

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div>
          <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
            Create your account
          </h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary-600 hover:text-primary-500">
              Sign in
            </Link>
          </p>
        </div>
        <form className="mt-8 space-y-6" action={formAction}>
          {!loading && state.error && (
            <div className="rounded-md bg-red-50 p-4">
              <p className="text-sm text-red-800">{state.error}</p>
            </div>
          )}
          <div className="space-y-4">
            <input
              name="username"
              type="text"
              required
              className="input"
              placeholder="Username"
              defaultValue={state.values.username}
            />
            <input
              name="email"
              type="email"
              required
              className="input"
              placeholder="Email"
              defaultValue={state.values.email}
            />
            <input
              name="about"
              type="text"
              className="input"
              placeholder="About (optional)"
              defaultValue={state.values.about}
            />
            <input
              name="password"
              type="password"
              required
              className="input"
              placeholder="Password"
              defaultValue={state.values.password}
            />
            <input
              name="confirmPassword"
              type="password"
              required
              className="input"
              placeholder="Confirm Password"
              defaultValue={state.values.confirmPassword}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full btn btn-primary disabled:opacity-50"
          >
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default Register;
