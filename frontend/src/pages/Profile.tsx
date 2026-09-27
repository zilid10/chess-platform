import { useState, useEffect } from 'react';
import { apiErrorMessage } from '../services/errors';
import { useAuth } from '../context/AuthContext';
import { userService } from '../services/userService';
import RatingsCard from '../components/RatingsCard';
import { User } from '../types';
import { Edit2, Save, X } from 'lucide-react';

const Profile = () => {
  const { user: currentUser, refreshUser } = useAuth();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [formData, setFormData] = useState({
    email: '',
    about: '',
    password: '',
    confirmPassword: '',
  });

  const loadUserProfile = async () => {
    if (!currentUser) {
      setLoading(false);
      return;
    }
    
    try {
      setLoading(true);
      setError('');
      const profile = await userService.getCurrentUser();
      setUser(profile);
      setFormData({
        email: profile.email || '',
        about: profile.about || '',
        password: '',
        confirmPassword: '',
      });
    } catch (err) {
      console.error('Error loading profile:', err);
      // Don't show error, just use currentUser data
      setUser(currentUser);
      setFormData({
        email: currentUser.email || '',
        about: currentUser.about || '',
        password: '',
        confirmPassword: '',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUserProfile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
    setError('');
    setSuccess('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (formData.password && formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (!currentUser?.id) return;

    setSaving(true);
    try {
      await userService.updateUser({
        email: formData.email,
        about: formData.about || undefined,
        rawPassword: formData.password || undefined,
      });
      
      setSuccess('Profile updated successfully!');
      setEditing(false);
      setFormData({ ...formData, password: '', confirmPassword: '' });
      await refreshUser(); // Refresh the auth context
      await loadUserProfile();
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to update profile'));
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    if (user) {
      setFormData({
        email: user.email,
        about: user.about || '',
        password: '',
        confirmPassword: '',
      });
    }
    setEditing(false);
    setError('');
    setSuccess('');
  };

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading profile...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8 flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Profile</h1>
          <p className="mt-2 text-gray-600">Manage your account settings</p>
        </div>
        {!editing && (
          <button
            onClick={() => setEditing(true)}
            className="btn btn-primary flex items-center space-x-2"
          >
            <Edit2 size={16} />
            <span>Edit Profile</span>
          </button>
        )}
      </div>

      {error && (
        <div className="mb-6 rounded-md bg-red-50 p-4">
          <p className="text-sm text-red-800">{error}</p>
        </div>
      )}

      {success && (
        <div className="mb-6 rounded-md bg-green-50 p-4">
          <p className="text-sm text-green-800">{success}</p>
        </div>
      )}

      <div className="card">
        <form onSubmit={handleSubmit}>
          <div className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Username
              </label>
              <input
                type="text"
                value={user?.username || ''}
                disabled
                className="input bg-gray-100 cursor-not-allowed"
              />
              <p className="mt-1 text-xs text-gray-500">Username cannot be changed</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Email
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                disabled={!editing}
                className={`input ${!editing ? 'bg-gray-100 cursor-not-allowed' : ''}`}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                About
              </label>
              <input
                type="text"
                name="about"
                value={formData.about}
                onChange={handleChange}
                disabled={!editing}
                placeholder="Optional - Tell us about yourself"
                className={`input ${!editing ? 'bg-gray-100 cursor-not-allowed' : ''}`}
              />
            </div>

            {editing && (
              <>
                <div className="border-t pt-6">
                  <h3 className="text-lg font-semibold mb-4">Change Password</h3>
                  <p className="text-sm text-gray-600 mb-4">Leave blank to keep current password</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    New Password
                  </label>
                  <input
                    type="password"
                    name="password"
                    value={formData.password}
                    onChange={handleChange}
                    placeholder="Enter new password"
                    className="input"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Confirm New Password
                  </label>
                  <input
                    type="password"
                    name="confirmPassword"
                    value={formData.confirmPassword}
                    onChange={handleChange}
                    placeholder="Confirm new password"
                    className="input"
                  />
                </div>
              </>
            )}

            {user?.createdAt && (
              <div className="border-t pt-6">
                <p className="text-sm text-gray-600">
                  <span className="font-semibold">Member since:</span>{' '}
                  {new Date(user.createdAt).toLocaleDateString('en-US', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                </p>
              </div>
            )}
          </div>

          {editing && (
            <div className="mt-8 flex space-x-4">
              <button
                type="submit"
                disabled={saving}
                className="btn btn-primary flex items-center space-x-2 disabled:opacity-50"
              >
                <Save size={16} />
                <span>{saving ? 'Saving...' : 'Save Changes'}</span>
              </button>
              <button
                type="button"
                onClick={handleCancel}
                disabled={saving}
                className="btn btn-secondary flex items-center space-x-2"
              >
                <X size={16} />
                <span>Cancel</span>
              </button>
            </div>
          )}
        </form>
      </div>

      {user?.id && (
        <div className="mt-6">
          <RatingsCard userId={user.id} />
        </div>
      )}
    </div>
  );
};

export default Profile;
